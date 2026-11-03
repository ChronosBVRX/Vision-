package com.chronosbvrx.visionplus.ui.player

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Cableado de Picture-in-Picture que no se puede probar en JVM: el reproductor nativo vive
 * en `:x4k` y el ExoPlayer dentro de `MainActivity`. Los guardianes fijan las piezas que,
 * si se pierden en un refactor, dejan el cuadro sin video, sin controles o con el menú
 * completo encogido.
 */
class PipWiringTest {

    @Test
    fun bothPlayerSurfacesDeclarePip() {
        val manifest = requireSource("src/main/AndroidManifest.xml")
        val main = manifest.indexOf("android:name=\".MainActivity\"")
        assertTrue("existe MainActivity", main >= 0)
        val mainBlock = manifest.substring(main, minOf(manifest.length, main + 600))
        assertTrue(
            "MainActivity (ExoPlayer) declara PiP",
            mainBlock.contains("android:supportsPictureInPicture=\"true\""),
        )
        val x4k = manifest.indexOf("android:name=\".ui.player.X4KPlayerAty\"")
        assertTrue("existe la activity nativa", x4k >= 0)
        val block = manifest.substring(x4k, minOf(manifest.length, x4k + 600))
        assertTrue("el reproductor nativo declara PiP", block.contains("android:supportsPictureInPicture=\"true\""))
    }

    @Test
    fun theNativePlayerEntersPipAndNeverParksWhileVisible() {
        val player = requireSource("src/main/java/com/chronosbvrx/visionplus/ui/player/X4KPlayerAty.kt")
        assertTrue("auto-entrada en Android 8–11", player.contains("override fun onUserLeaveHint()"))
        assertTrue("cambio de modo PiP", player.contains("override fun onPictureInPictureModeChanged("))
        assertTrue("receptor del play/pausa", player.contains("PipMode.register(this, receiver)"))
        assertTrue("entrada explícita", player.contains("PipMode.enter(this, playing)"))
        assertTrue("UI mínima en el cuadro", player.contains("private fun applyPipUi("))
        assertTrue("botón PiP en móvil", player.contains("pill(\"PiP\""))
        val stop = player.indexOf("override fun onStop()")
        assertTrue("onStop existe", stop >= 0)
        val stopBody = player.substring(stop, minOf(player.length, stop + 500))
        assertTrue(
            "en PiP no se suelta el motor",
            stopBody.contains("isInPipNow() || pipActive || pipRequested"),
        )
    }

    @Test
    fun theComposePlayerHidesChromeInsidePip() {
        val screen = requireSource("src/main/java/com/chronosbvrx/visionplus/ui/player/PlayerScreen.kt")
        assertTrue("la sesión se registra sin cambiar la firma", screen.contains("PipSession.Active("))
        assertTrue("se da de baja al salir", screen.contains("PipSession.detach(session)"))
        assertTrue("lee el modo PiP", screen.contains("PipSession.inPip"))
        assertTrue("oculta controles y modales", screen.contains("if (!inPip)"))
        assertTrue("botón PiP móvil", screen.contains("PictureInPictureAlt"))
        assertTrue("entra al cuadro", screen.contains("PipMode.enter(it, playing)"))
        assertTrue(
            "el búnker solo quita FLAG_SECURE con permiso",
            screen.contains("VisionSettings.pipDownloadsAllowed(context)"),
        )
        assertTrue(
            "capa negra si se fuerza PiP sin permiso",
            screen.contains("if (inPip && isVault"),
        )
    }

    @Test
    fun theFeatureCanBeDisabledRemotely() {
        val pip = requireSource("src/main/java/com/chronosbvrx/visionplus/ui/player/PipMode.kt")
        assertTrue("kill-switch remoto", pip.contains("RemoteConfig.flag(context, \"pip_enabled\", true)"))
    }

    @Test
    fun theMainActivityOwnsTheSystemParams() {
        val main = requireSource("src/main/java/com/chronosbvrx/visionplus/MainActivity.kt")
        assertTrue("auto-entrada", main.contains("override fun onUserLeaveHint()"))
        assertTrue("cambio de modo", main.contains("override fun onPictureInPictureModeChanged("))
        assertTrue("solo con reproductor activo", main.contains("PipSession.canEnter(this)"))
        assertTrue("acciones y autoEnter", main.contains("PipMode.update(this, playing, autoEnter = canEnter)"))
        assertTrue("receptor play/pausa", main.contains("PipSession.toggle()"))
    }

    @Test
    fun theDownloadOptInIsExplicitAndOffByDefault() {
        val settings = requireSource("src/main/java/com/chronosbvrx/visionplus/ui/settings/VisionSettings.kt")
        assertTrue(settings.contains("KEY_PIP_DOWNLOADS"))
        assertTrue(settings.contains("getBoolean(KEY_PIP_DOWNLOADS, false)"))
        assertTrue(settings.contains("fun pipDownloadsAllowed(context: Context): Boolean"))
        val phone = requireSource("src/main/java/com/chronosbvrx/visionplus/ui/phone/SettingsScreen.kt")
        assertTrue(phone.contains("Picture-in-picture en descargas"))
        assertTrue(phone.contains("setPipDownloads(context, it)"))
    }

    private fun requireSource(path: String): String {
        val file = File(path)
        assertTrue("no existe $path (cwd=${File(".").absolutePath})", file.isFile)
        return file.readText()
    }
}
