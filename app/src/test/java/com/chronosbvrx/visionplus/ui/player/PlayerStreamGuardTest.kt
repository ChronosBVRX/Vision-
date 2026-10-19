package com.chronosbvrx.visionplus.ui.player

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guardián del arreglo «no es pausa, es un salto de frames»:
 *
 * 1. La «Calidad preferida» de Ajustes manda desde el PRIMER cuadro: el motor nativo arranca
 *    con la primera media de la lista (`X4KTitan.play` usa `medias.first().name`), así que la
 *    elegida se coloca al frente. Antes el ajuste solo pintaba la etiqueta y el motor seguía
 *    en la calidad del portal — bajar de calidad no aliviaba los saltos hasta cambiarla a mano.
 *
 * 2. El detector de saltos de contenido (`PLAYER_FRAME_SKIP`): la posición no puede avanzar
 *    más que el reloj de pared; el exceso se reporta al diagnóstico y los seeks legítimos de
 *    la app reinician la línea base para no confundirse con saltos del motor.
 */
class PlayerStreamGuardTest {

    // ---------------------------------------------------------------- calidad al arrancar

    private data class Media(val name: String, val quality: String)

    @Test
    fun thePreferredQualityGoesFirstAndTheRestKeepsItsOrder() {
        val medias = listOf(
            Media("a", "1080p"),
            Media("b", "720p"),
            Media("c", "480p"),
        )
        val ordered = orderMediasByQuality(medias, { it.quality }, "720p")
        assertEquals(listOf("b", "a", "c"), ordered.map { it.name })
    }

    @Test
    fun autoAndMissingQualitiesKeepThePortalOrder() {
        val medias = listOf(
            Media("a", "1080p"),
            Media("b", "720p"),
        )
        assertEquals(medias, orderMediasByQuality(medias, { it.quality }, "Auto"))
        assertEquals(medias, orderMediasByQuality(medias, { it.quality }, "auto"))
        assertEquals(medias, orderMediasByQuality(medias, { it.quality }, "4K"))
        assertEquals(medias, orderMediasByQuality(medias, { it.quality }, ""))
    }

    @Test
    fun aSingleOrAlreadyFirstMediaIsLeftAlone() {
        assertEquals(
            listOf(Media("a", "720p")),
            orderMediasByQuality(listOf(Media("a", "720p")), { it.quality }, "1080p"),
        )
        val medias = listOf(Media("a", "720p"), Media("b", "1080p"))
        assertEquals(medias, orderMediasByQuality(medias, { it.quality }, "720p"))
    }

    // ---------------------------------------------------------------- detector de saltos

    @Test
    fun aNormalTickIsNotASkip() {
        // 500 ms de reloj y la posición avanza lo mismo.
        assertEquals(0L, positionSkipExcessMs(1_000L, 10_000L, 1_500L, 10_500L))
        // Jitter normal (±300 ms del motor) tampoco.
        assertEquals(0L, positionSkipExcessMs(1_000L, 10_000L, 1_800L, 10_500L))
    }

    @Test
    fun aStallOrARewindIsNotASkip() {
        assertEquals(0L, positionSkipExcessMs(1_000L, 10_000L, 1_000L, 10_500L))
        assertEquals(0L, positionSkipExcessMs(1_000L, 10_000L, 400L, 10_500L))
    }

    @Test
    fun aLostTickIsNotASkip() {
        // 6 s sin sondeo (app en pausa, proceso dormido): la base no vale.
        assertEquals(0L, positionSkipExcessMs(1_000L, 10_000L, 7_000L, 16_000L))
    }

    @Test
    fun aRealContentJumpIsFlaggedWithItsExcess() {
        // El caso medido en la prueba del usuario: la posición salta ~1.5 s más que el reloj.
        assertEquals(1_500L, positionSkipExcessMs(1_000L, 10_000L, 3_000L, 10_500L))
        // Un salto de 5 s reporta 5 s de exceso.
        assertEquals(5_000L, positionSkipExcessMs(0L, 10_000L, 5_500L, 10_500L))
    }

    @Test
    fun theToleranceEdgeIsNotFlagged() {
        // 800 ms de exceso (< 900): dentro de la tolerancia.
        assertEquals(0L, positionSkipExcessMs(1_000L, 10_000L, 2_300L, 10_500L))
        // 1.1 s de exceso: fuera de la tolerancia.
        assertEquals(1_100L, positionSkipExcessMs(1_000L, 10_000L, 2_600L, 10_500L))
    }

    @Test
    fun noBaselineMeansNoReading() {
        assertEquals(0L, positionSkipExcessMs(-1L, 0L, 5_000L, 10_500L))
        assertEquals(0L, positionSkipExcessMs(-1L, 10_000L, 5_000L, 10_500L))
    }

    // ---------------------------------------------------------------- cableado

    @Test
    fun theNativePlayerStartsOnTheOrderedQualityAndReportsContentJumps() {
        val player = requireSource("src/main/java/com/chronosbvrx/visionplus/ui/player/X4KPlayerAty.kt")
        // El arranque (mountUi) y el cambio de capítulo (playEpisode, con su reintento) usan la
        // lista ordenada por la calidad preferida.
        val ordering = Regex("orderMediasByQuality\\(").findAll(player).count()
        assertTrue("el reproductor ordena las medias por calidad ($ordering)", ordering >= 3)
        assertTrue("el play de arranque usa la lista ordenada", player.contains("X4KTitan.play(v, mode, code, title, orderedMedias)"))
        // El salto de contenido se detecta y se reporta.
        assertTrue("telemetría del salto", player.contains("\"PLAYER_FRAME_SKIP\""))
        assertTrue("detector puro", player.contains("positionSkipExcessMs("))
        // Los seeks de la app reinician la base (sin falsos positivos).
        assertTrue("reinicio tras seek", player.contains("noteAppSeek()"))
        val seeks = Regex("noteAppSeek\\(\\)").findAll(player).count()
        assertTrue("cada seek legítimo lo anota ($seeks)", seeks >= 5)
        // Higiene: el icono de play/pausa ya no se reconstruye dos veces por segundo.
        assertTrue("icono con estado cacheado", player.contains("playIconState"))
    }

    private fun requireSource(rel: String): String {
        val found = listOf(File("app/$rel"), File(rel), File("../app/$rel")).firstOrNull { it.exists() }
        assertNotNull("fuente no encontrada: $rel", found)
        return found!!.readText()
    }
}
