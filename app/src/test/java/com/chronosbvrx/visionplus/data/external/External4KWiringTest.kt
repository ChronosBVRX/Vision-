package com.chronosbvrx.visionplus.data.external

import java.io.File
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Guardianes de cableado: módulo, ficha, reproductor y worker. */
class External4KWiringTest {

    @Test
    fun thePlayerUsesAFreshExternalSourceAndFallsBackToNative() {
        val player = source("app/src/main/java/com/chronosbvrx/visionplus/ui/player/PlayerScreen.kt")
        assertTrue(player.contains("External4KRepository.fresh"))
        assertTrue(player.contains("External4KPlayback"))
        assertTrue(player.contains("skipExternal = true"))
        assertTrue(player.contains("\"EXTERNAL_4K_FALLBACK\""))
        assertTrue(player.contains("onExternalFatal"))
    }

    @Test
    fun aMoviesSyntheticEpisodeStillUsesTheExternalSource() {
        val player = source("app/src/main/java/com/chronosbvrx/visionplus/ui/player/PlayerScreen.kt")
        assertTrue("la ficha de película manda un EpisodePlay sintético", player.contains("moviePlay"))
        assertTrue(player.contains("episode.code == film.videoUrl.substringAfter(':'"))
    }

    @Test
    fun thePlayerTurnsOnSpanishSubtitlesWhenTheAudioIsNotSpanish() {
        val player = source("app/src/main/java/com/chronosbvrx/visionplus/ui/player/PlayerScreen.kt")
        assertTrue(player.contains("autoSelectSpanishSubtitles"))
        assertTrue(player.contains("isSpanishLanguage"))
        assertTrue(player.contains("setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)"))
    }

    @Test
    fun theNative4kKeepsTheLanguageAndSubtitlePickersAndTheAvailableBadge() {
        val aty = source("app/src/main/java/com/chronosbvrx/visionplus/ui/player/X4KPlayerAty.kt")
        assertTrue(aty.contains("externalAudioOptions"))
        assertTrue(aty.contains("externalSubtitleOptions"))
        assertTrue(aty.contains("4K disponible"))
        assertTrue(aty.contains("fourKPoller"))
        assertTrue(aty.contains("External4KStore"))
        val api = source("app/src/main/java/com/chronosbvrx/visionplus/data/xuper/X4KApi.kt")
        assertTrue(api.contains("external4kLanguage"))
        val worker = source("app/src/main/java/com/chronosbvrx/visionplus/data/external/External4KWorker.kt")
        assertTrue(worker.contains("fun refreshSoon"))
        val screen = source("app/src/main/java/com/chronosbvrx/visionplus/ui/player/PlayerScreen.kt")
        assertTrue(screen.contains("refreshSoon"))
    }

    @Test
    fun thePlayerPrefersSpanishAudioThenEnglish() {
        val player = source("app/src/main/java/com/chronosbvrx/visionplus/ui/player/PlayerScreen.kt")
        assertTrue(player.contains("setPreferredAudioLanguages(\"es\", \"spa\", \"en\", \"eng\")"))
    }

    @Test
    fun theNativePlayerOpensTheExternal4kAsAUrlQuality() {
        val aty = source("app/src/main/java/com/chronosbvrx/visionplus/ui/player/X4KPlayerAty.kt")
        assertTrue(aty.contains("isExternal4k"))
        assertTrue(aty.contains("startSpecs(v, orderedMedias, code, title)"))
        assertTrue(aty.contains("startExternal(external)"))
        assertTrue(aty.contains("4K externo en reproductor nativo"))
        assertTrue(aty.contains("androidx.media3.exoplayer.ExoPlayer"))
        assertTrue(aty.contains("if (isExternal4k(target))"))
    }

    @Test
    fun the4kPlayerOffersLowerQualitiesInTheSamePlayer() {
        val player = source("app/src/main/java/com/chronosbvrx/visionplus/ui/player/PlayerScreen.kt")
        assertTrue(player.contains("External4KRepository.lowerQualities"))
        assertTrue(player.contains("externalQualities = qualities"))
        assertTrue(player.contains("\"EXTERNAL_4K_QUALITY\""))
        val dialogs = source("app/src/main/java/com/chronosbvrx/visionplus/ui/player/PlayerDialogs.kt")
        assertTrue(dialogs.contains("onPickExternalQuality"))
        assertTrue(dialogs.contains("Calidad nativa (Xuper)"))
        val parser = source("app/src/main/java/com/chronosbvrx/visionplus/data/external/External4KParser.kt")
        assertTrue(parser.contains("fun parseHlsQualities"))
    }

    @Test
    fun bothTitleSheetsRefreshAndShowTheBrandedBadge() {
        for (path in listOf(
            "app/src/main/java/com/chronosbvrx/visionplus/ui/title/TitleScreen.kt",
            "app/src/main/java/com/chronosbvrx/visionplus/ui/tv/TvTitleScreen.kt",
        )) {
            val src = source(path)
            assertTrue("$path refresca", src.contains("External4KRepository.refreshIfStale"))
            assertTrue("$path muestra el badge", src.contains("FourKBadge("))
        }
        val badge = source("app/src/main/java/com/chronosbvrx/visionplus/ui/components/FourKBadge.kt")
        assertTrue(badge.contains("VisionBlue"))
        assertTrue(badge.contains("ULTRA HD"))
    }

    @Test
    fun aDeepLinkedFilmWithoutKindStillShowsAndPlaysTheExternalSource() {
        for (path in listOf(
            "app/src/main/java/com/chronosbvrx/visionplus/ui/title/TitleScreen.kt",
            "app/src/main/java/com/chronosbvrx/visionplus/ui/tv/TvTitleScreen.kt",
            "app/src/main/java/com/chronosbvrx/visionplus/ui/player/PlayerScreen.kt",
        )) {
            val src = source(path)
            assertTrue(
                "$path debe aceptar kind vacío (deep links del launcher)",
                src.contains("kind.isBlank() || ") && src.contains("kind == \"movie\""),
            )
        }
    }

    @Test
    fun theResolverUsesTheVpsBeforeTouchingThePublicAddon() {
        val repo = source("app/src/main/java/com/chronosbvrx/visionplus/data/external/External4KRepository.kt")
        assertTrue(repo.contains("/v1/4k?"))
        assertTrue(repo.contains("tmdb="))
        assertTrue(repo.contains("title="))
        assertTrue(repo.contains("RemoteIndexSource.API_KEY"))
    }

    @Test
    fun theAppSchedulesThePeriodicWorkerAndBundlesWorkManager() {
        val app = source("app/src/main/java/com/chronosbvrx/visionplus/VisionApp.kt")
        assertTrue(app.contains("External4KWorker.ensure"))
        val gradle = source("app/build.gradle.kts")
        assertTrue(gradle.contains("androidx.work:work-runtime-ktx"))
        val worker = source("app/src/main/java/com/chronosbvrx/visionplus/data/external/External4KWorker.kt")
        assertTrue(worker.contains("enqueueUniquePeriodicWork"))
        assertTrue(worker.contains("6, TimeUnit.HOURS"))
    }

    private fun source(rel: String): String {
        val found = listOf(File(rel), File("../$rel"), File("Vision-/$rel")).firstOrNull { it.exists() }
        assertNotNull("fuente no encontrada: $rel", found)
        return found!!.readText()
    }
}
