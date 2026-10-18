package com.chronosbvrx.visionplus.data.remote

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * El cableado de la memoria de vínculos (lo que las pruebas puras no alcanzan a componer):
 * - La tele anuncia su identidad estable y recuerda los nombres de los dispositivos.
 * - Ajustes de la tele enseña los dispositivos vinculados y deja olvidarlos.
 * - La pantalla del teléfono lista «Tus teles vinculadas» y las rotula como tales.
 */
class RemoteMemoryWiringTest {

    @Test
    fun theTvAnnouncesItsStableIdentityAndRemembersDevices() {
        val text = requireSource("src/main/java/com/chronosbvrx/visionplus/data/remote/RemoteServer.kt")
        assertTrue(text.contains("tvIdOf("))
        assertTrue("la baliza viaja con la identidad", text.contains("RemoteDiscovery.beaconLine(deviceName, deviceModel, RemoteDiscovery.CONTROL_PORT, tvId)"))
        assertTrue("el nombre queda guardado junto al token", text.contains("saveDeviceName(token, name)"))
        assertTrue(text.contains("rememberedDevices("))
    }

    @Test
    fun theTvSettingsShowAndForgetTheLinkedDevices() {
        val text = requireSource("src/main/java/com/chronosbvrx/visionplus/ui/tv/TvSettingsScreen.kt")
        assertTrue(text.contains("Control remoto"))
        assertTrue(text.contains("Dispositivos vinculados"))
        assertTrue(text.contains("RemoteServer.forgetPairings("))
    }

    @Test
    fun theRemoteScreenShowsTheLinkedTvs() {
        val text = requireSource("src/main/java/com/chronosbvrx/visionplus/ui/remote/RemoteScreen.kt")
        assertTrue(text.contains("Tus teles vinculadas"))
        assertTrue(text.contains("rememberedTvs("))
        assertTrue(text.contains("tvRowLabel("))
        assertTrue("conectar nunca pide PIN si ya está vinculada", text.contains("isPaired(context, last)"))
    }

    private fun requireSource(rel: String): String {
        val found = listOf(File("app/$rel"), File(rel), File("../app/$rel")).firstOrNull { it.exists() }
        assertNotNull("fuente no encontrada: $rel", found)
        return found!!.readText()
    }
}
