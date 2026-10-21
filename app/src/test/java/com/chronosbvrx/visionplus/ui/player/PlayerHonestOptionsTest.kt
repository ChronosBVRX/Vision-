package com.chronosbvrx.visionplus.ui.player

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guardián de las opciones HONESTAS del reproductor:
 *
 * el título solo puede ofrecer lo que de verdad trae. Con un único idioma no hay píldora de
 * audio; sin subtítulos no hay píldora de subtítulos; con una sola resolución no hay píldora
 * de calidad. Antes las tres salían siempre (y el menú de ExoPlayer era de relleno: «Servidor»
 * falso, «Original», «este archivo no trae subtítulos»), así que se abrían menús vacíos o de
 * una sola entrada.
 */
class PlayerHonestOptionsTest {

    // ---------------------------------------------------------------- decisión pura

    @Test
    fun aSingleAudioLanguageDoesNotOfferAPicker() {
        assertFalse(shouldShowAudioPicker(emptyList(), 0))
        assertFalse(shouldShowAudioPicker(listOf("Español"), 1))
        // El portal repite el idioma: sigue siendo una sola opción real.
        assertFalse(shouldShowAudioPicker(listOf("Español", "español", " "), 1))
    }

    @Test
    fun twoRealAudioLanguagesOfferThePicker() {
        assertTrue(shouldShowAudioPicker(listOf("Español", "Inglés"), 0))
        // Aunque el portal no declare, si el motor expone dos pistas hay algo que elegir.
        assertTrue(shouldShowAudioPicker(emptyList(), 2))
    }

    @Test
    fun subtitlesOnlyAppearWhenTheProgramBringsAtLeastOne() {
        assertFalse(shouldShowSubtitlePicker(loaded = false, subtitleEntries = 3))
        assertFalse(shouldShowSubtitlePicker(loaded = true, subtitleEntries = 0))
        assertTrue(shouldShowSubtitlePicker(loaded = true, subtitleEntries = 1))
    }

    @Test
    fun qualityOnlyAppearsWithMoreThanOneRealResolution() {
        assertFalse(shouldShowQualityPicker(0))
        assertFalse(shouldShowQualityPicker(1))
        assertTrue(shouldShowQualityPicker(2))
    }

    @Test
    fun copiesOfTheSameResolutionCountAsOneChoice() {
        data class Media(val name: String, val quality: String)
        val medias = listOf(
            Media("licencia-a", "1080p"),
            Media("licencia-b", "1080p"),
            Media("espejo", "FHD"),
            Media("baja", "480p"),
            Media("sin-etiqueta-1", ""),
            Media("sin-etiqueta-2", ""),
        )
        val choices = distinctQualityChoices(medias, { it.quality }, { it.name })
        // «FHD» se compara tal cual (el portal suele mandar la misma cadena); «1080p» es una
        // sola; las dos sin etiqueta son streams distintos y no se fusionan.
        assertEquals(5, choices.size)
    }

    @Test
    fun theExoPlayerQualityLabelsSpeakTheRealResolution() {
        assertEquals("4K Ultra HD", videoQualityLabel(2160))
        assertEquals("1080p FHD", videoQualityLabel(1080))
        assertEquals("720p HD", videoQualityLabel(720))
        assertEquals("480p SD", videoQualityLabel(480))
        assertEquals("360p", videoQualityLabel(360))
        assertEquals("2160p", videoQualityKey(2160))
        assertEquals("1080p", videoQualityKey(1080))
        assertEquals("720p", videoQualityKey(720))
        assertEquals("480p", videoQualityKey(480))
    }

    // ---------------------------------------------------------------- cableado

    @Test
    fun theNativePlayerHidesThePillsWithoutRealOptions() {
        val player = requireSource("src/main/java/com/chronosbvrx/visionplus/ui/player/X4KPlayerAty.kt")
        assertTrue("re-evalúa las píldoras", player.contains("applyOptionPillVisibility()"))
        assertTrue("audio honesto", player.contains("shouldShowAudioPicker("))
        assertTrue("subtítulos honestos", player.contains("shouldShowSubtitlePicker("))
        assertTrue("calidad honesta", player.contains("shouldShowQualityPicker("))
        assertTrue("una píldora oculta no deja el foco muerto", player.contains("rewireOptionPillFocus()"))
        // El menú de audio no se abre con una sola pista del motor.
        assertTrue("audio con más de una pista", player.contains("if (engineTracks.size > 1)"))
    }

    @Test
    fun theExoPlayerMenuIsBuiltFromTheRealTracks() {
        val screen = requireSource("src/main/java/com/chronosbvrx/visionplus/ui/player/PlayerScreen.kt")
        assertTrue("escucha las pistas reales", screen.contains("onTracksChanged"))
        assertTrue("las reúne", screen.contains("refreshTrackChoices("))
        assertTrue("aplica el audio elegido", screen.contains("applyAudioChoice("))
        assertTrue("aplica los subtítulos elegidos", screen.contains("applySubtitleChoice("))
        assertTrue("el botón solo existe con opciones", screen.contains("menu.hasAnyChoices"))

        val dialogs = requireSource("src/main/java/com/chronosbvrx/visionplus/ui/player/PlayerDialogs.kt")
        assertFalse("fuera el «Servidor» placebo", dialogs.contains("Opción 1") || dialogs.contains("tryingServerNote"))
        assertTrue("secciones condicionadas al audio real", dialogs.contains("state.hasAudioChoices"))
        assertTrue("secciones condicionadas a los subtítulos reales", dialogs.contains("state.hasSubtitleChoices"))
        assertTrue("secciones condicionadas a las resoluciones reales", dialogs.contains("state.hasQualityChoices"))
    }

    private fun requireSource(rel: String): String {
        val found = listOf(File("app/$rel"), File(rel), File("../app/$rel")).firstOrNull { it.exists() }
        assertNotNull("fuente no encontrada: $rel", found)
        return found!!.readText()
    }
}
