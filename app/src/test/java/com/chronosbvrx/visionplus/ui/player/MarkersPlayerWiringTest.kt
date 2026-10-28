package com.chronosbvrx.visionplus.ui.player

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * El cableado del motor de marcadores que no se puede componer en la suite (el
 * reproductor nativo vive en `:x4k` y necesita el motor del proveedor):
 * - cada capítulo pide SU plan (nunca reutiliza el del primero de la temporada);
 * - «Omitir intro» no se pinta hasta que el video está dibujado (fin de la carga);
 * - el reproductor Compose ya no salta 85 s a ciegas ni muestra el diálogo placebo.
 */
class MarkersPlayerWiringTest {

    @Test
    fun everyEpisodeFetchesItsOwnPlan() {
        val player = requireSource("src/main/java/com/chronosbvrx/visionplus/ui/player/X4KPlayerAty.kt")
        assertTrue("el capítulo nuevo prepara sus marcadores", player.contains("beginMarkersForEpisode("))
        assertTrue("el reproductor pide el plan por red/disco", player.contains("MarkersStore.fetchForPlayer("))
        assertTrue("el arranque pide el plan si el intent no lo trae", player.contains("fetchMarkersForCurrent(playGen)"))
        assertFalse("no se reutiliza la ventana del primer capítulo", player.contains("keepMarkersForSeason("))
    }

    @Test
    fun theSkipPillWaitsForTheVideoToBeDrawn() {
        val player = requireSource("src/main/java/com/chronosbvrx/visionplus/ui/player/X4KPlayerAty.kt")
        val start = player.indexOf("private fun updateIntroPill(")
        assertTrue("updateIntroPill existe", start >= 0)
        val body = player.substring(start, minOf(player.length, start + 1_600))
        assertTrue("usa la condición de video listo", body.contains("videoIsReady()"))
        assertTrue("la píldora pasa por la política única", body.contains("canShowSkipPill("))
    }

    @Test
    fun thePlaceholderCorrectionDialogIsGone() {
        // Decisión del usuario: cero trabajo manual. El diálogo que solo aparentaba
        // corregir intro/outro ya no existe en el camino de ExoPlayer.
        val dialogs = requireSource("src/main/java/com/chronosbvrx/visionplus/ui/player/PlayerDialogs.kt")
        assertFalse(dialogs.contains("SkipMarkersDialog"))
        assertFalse(dialogs.contains("Corregir intro y outro"))
    }

    @Test
    fun theComposePlayerUsesRealMarkersAndTheSamePolicy() {
        val screen = requireSource("src/main/java/com/chronosbvrx/visionplus/ui/player/PlayerScreen.kt")
        assertTrue("auto-skip por política", screen.contains("shouldApplyAutoSkip("))
        assertTrue("el búnker lee sus marcadores", screen.contains("MarkersStore.cachedJson("))
        assertFalse("ya no hay salto fijo de 85 s", screen.contains("SKIP_INTRO_MS"))
        assertFalse("ya no está el diálogo placebo", screen.contains("SkipMarkersDialog("))
    }

    @Test
    fun thePortalLaunchShipsCachedMarkersWithoutBlocking() {
        val api = requireSource("src/main/java/com/chronosbvrx/visionplus/data/xuper/X4KApi.kt")
        assertTrue("lee el plan cacheado al lanzar", api.contains("MarkersStore.cachedJson("))
        assertTrue("manda el ref/ep al reproductor", api.contains("markersRef = if (isSeries)"))
        assertFalse("el lanzamiento ya no espera al job de marcadores", api.contains("markersJob?.await()"))
    }

    @Test
    fun autoSkipOnlyRunsWhenTheUserChoseAutomatic() {
        // Pedido del usuario (post 2.5.113): «el omitir intro está funcionando solo sin
        // que lo presione». El default volvió a Botón y el salto automático SOLO puede
        // dispararse cuando el ajuste dice Automático — en los dos reproductores.
        val player = requireSource("src/main/java/com/chronosbvrx/visionplus/ui/player/X4KPlayerAty.kt")
        val nativeGate = player.indexOf("private fun maybeAutoSkipIntro(")
        assertTrue("maybeAutoSkipIntro existe", nativeGate >= 0)
        val nativeBody = player.substring(nativeGate, minOf(player.length, nativeGate + 400))
        assertTrue(
            "el nativo se detiene si el modo no es Automático",
            nativeBody.contains("skipIntroMode != com.chronosbvrx.visionplus.ui.settings.VisionSettings.SKIP_INTRO_AUTO) return"),
        )
        val compose = requireSource("src/main/java/com/chronosbvrx/visionplus/ui/player/PlayerScreen.kt")
        val composeGate = compose.indexOf("val skipMode =")
        assertTrue("el Compose lee el ajuste", composeGate >= 0)
        val composeBody = compose.substring(composeGate, minOf(compose.length, composeGate + 2_400))
        assertTrue(
            "el Compose se detiene si el modo no es Automático",
            composeBody.contains("VisionSettings.SKIP_INTRO_AUTO) return@LaunchedEffect"),
        )
    }

    private fun requireSource(path: String): String {
        val file = File(path)
        assertTrue("no existe $path (cwd=${File(".").absolutePath})", file.isFile)
        return file.readText()
    }
}
