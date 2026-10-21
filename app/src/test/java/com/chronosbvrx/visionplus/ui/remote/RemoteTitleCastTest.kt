package com.chronosbvrx.visionplus.ui.remote

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * El botón «Ver en la TV» de la ficha móvil: lleva el título que estás viendo a la tele desde el
 * teléfono. Se inspecciona la fuente porque es un botón dentro de una pantalla grande (Compose);
 * el contrato que blinda: manda abrir con el control remoto, exige una referencia que sepamos
 * abrir y solo aparece con la conexión VIVA — nunca un botón muerto.
 */
class RemoteTitleCastTest {

    @Test
    fun thePhoneFichaOffersCastOnlyWhenTheRemoteIsConnected() {
        val text = requireSource("src/main/java/com/chronosbvrx/visionplus/ui/title/TitleScreen.kt")
        assertTrue("la ficha ofrece «Ver en la TV»", text.contains("\"Ver en la TV\""))
        assertTrue("la orden viaja por el control remoto", text.contains("RemoteClient.sendOpen("))
        assertTrue("solo con una referencia que sepamos abrir", text.contains("castableRef(film.videoUrl)"))
        assertTrue("solo con la conexión viva", text.contains("RemoteStatus.CONNECTED"))
        assertTrue("manda el minuto exacto del teléfono", text.contains("resumePositionFor(context"))
    }

    private fun requireSource(rel: String): String {
        val found = listOf(File("app/$rel"), File(rel), File("../app/$rel")).firstOrNull { it.exists() }
        assertNotNull("fuente no encontrada: $rel", found)
        return found!!.readText()
    }
}
