package com.chronosbvrx.visionplus.ui.remote

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * El cableado del «el teléfono manda algo mientras la tele reproduce» (no se puede componer
 * sin red/físico, así que las fuentes son el guardián):
 * - El deep link del control remoto REEMPLAZA el reproductor en curso (jamás lo apila).
 * - Al cerrar un reproductor solo se cierra SU ruta: la espera vieja no puede deshacer la
 *   reproducción que el teléfono acaba de mandar.
 * - Si el reproductor queda detrás con reproducción viva, el motor se suelta (fin del audio
 *   invisible en el menú).
 */
class RemotePlaybackWiringTest {

    @Test
    fun theRemoteDeepLinkReplacesThePlayerInProgress() {
        val activity = requireSource("src/main/java/com/chronosbvrx/visionplus/MainActivity.kt")
        assertTrue(
            "el deep link (control remoto / launcher) entra con pushPlayer, no con push",
            activity.contains("nav.pushPlayer(it)"),
        )
        val nav = requireSource("src/main/java/com/chronosbvrx/visionplus/ui/Navigation.kt")
        assertTrue("pushPlayer reemplaza el reproductor de arriba", nav.contains("fun pushPlayer(route: Route)"))
        assertTrue("close existe y es por ruta", nav.contains("fun close(route: Route)"))
    }

    @Test
    fun theScreensCloseOnlyTheirOwnPlayerRoute() {
        val tv = requireSource("src/main/java/com/chronosbvrx/visionplus/ui/TvApp.kt")
        assertTrue(
            "TV: Player y vivo cierran su propia ruta",
            tv.split("closePlayback(route, nav, onExitToLauncher)").size - 1 >= 2,
        )
        val phone = requireSource("src/main/java/com/chronosbvrx/visionplus/ui/PhoneApp.kt")
        assertTrue(
            "móvil: Player y vivo cierran su propia ruta",
            phone.split("closePlayback(route, nav, onExitToLauncher)").size - 1 >= 2,
        )
    }

    @Test
    fun thePhoneShowsTheRealNowPlayingCardAndSendsTypedControls() {
        val remote = requireSource("src/main/java/com/chronosbvrx/visionplus/ui/remote/RemoteScreen.kt")
        assertTrue("la tarjeta «Ahora en la tele» existe", remote.contains("NowPlayingCard"))
        assertTrue("la barra manda un seek absoluto", remote.contains("RemoteClient.sendSeek"))
        assertTrue("play/pausa/siguiente van como órdenes", remote.contains("RemoteClient.sendCommand"))
        assertTrue("el vivo no ofrece barra de búsqueda", remote.contains("if (np.live)"))
        assertTrue("audio/subtítulos se abren desde el teléfono", remote.contains("CMD_AUDIO"))
        assertTrue(remote.contains("CMD_SUBTITLES"))
        val phone = requireSource("src/main/java/com/chronosbvrx/visionplus/ui/PhoneApp.kt")
        assertTrue("la barra persistente usa el estado real", phone.contains("RemoteNowBar"))
    }

    @Test
    fun theTvPublishesItsPlaybackOverTheMessengerBridge() {
        val player = requireSource("src/main/java/com/chronosbvrx/visionplus/ui/player/X4KPlayerAty.kt")
        assertTrue("el nativo instala el puente", player.contains("RemoteBridge.PlayerLink"))
        assertTrue("publica estado minuto a minuto", player.contains("publishRemoteState"))
        assertTrue("construye el estado estructurado", player.contains("NowPlaying("))
        val composed = requireSource("src/main/java/com/chronosbvrx/visionplus/ui/player/PlayerScreen.kt")
        assertTrue("ExoPlayer también publica", composed.contains("RemoteBus.publishPlayback"))
        assertTrue("ExoPlayer recibe las órdenes en el hilo de UI", composed.contains("RemoteBus.attachPlayer"))
        val server = requireSource("src/main/java/com/chronosbvrx/visionplus/data/remote/RemoteServer.kt")
        assertTrue("el estado solo va a teléfonos autorizados", server.contains("filter { it.authorized }"))
        assertTrue("el servidor manda el estado estructurado", server.contains("playback.asMsg()"))
    }

    @Test
    fun thePlayerReleasesTheEngineWhenItGoesBehind() {
        val player = requireSource("src/main/java/com/chronosbvrx/visionplus/ui/player/X4KPlayerAty.kt")
        assertTrue(
            "onStop suelta el motor cuando quedó reproducción viva detrás",
            player.contains("shouldParkPlaybackOnStop(isPlayerActive, exiting)"),
        )
        assertTrue("el motor se detiene al estacionar", player.contains("parkPlaybackInBackground()"))
    }

    private fun requireSource(rel: String): String {
        val found = listOf(File("app/$rel"), File(rel), File("../app/$rel")).firstOrNull { it.exists() }
        assertNotNull("fuente no encontrada: $rel", found)
        return found!!.readText()
    }
}
