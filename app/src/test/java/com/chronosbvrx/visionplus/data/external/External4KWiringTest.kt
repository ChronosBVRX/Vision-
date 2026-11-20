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
        assertTrue(aty.contains("disponible en 4K en su idioma original"))
        assertTrue(aty.contains("fourKPoller"))
        assertTrue(aty.contains("External4KStore"))
        val api = source("app/src/main/java/com/chronosbvrx/visionplus/data/xuper/X4KApi.kt")
        assertTrue(api.contains("external4kLanguage"))
        val worker = source("app/src/main/java/com/chronosbvrx/visionplus/data/external/External4KWorker.kt")
        assertTrue(worker.contains("fun refreshSoon"))
        // El 4K NO se resuelve en la ficha: espera a que la pel?cula ya est? reproduciendo
        // para que su red no choque con el arranque del 1080p.
        val title = source("app/src/main/java/com/chronosbvrx/visionplus/ui/tv/TvTitleScreen.kt")
        assertTrue("la ficha ya no resuelve 4K", !title.contains("refreshIfStale"))
        assertTrue("la ficha mantiene el badge informativo", title.contains("FourKBadge("))
        assertTrue("la ficha conserva el handshake del Play", title.contains("prewarmVod"))
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
    fun theNativePlayerAlwaysStartsOnTheBestNativeQualityAndKeeps4kManual() {
        val aty = source("app/src/main/java/com/chronosbvrx/visionplus/ui/player/X4KPlayerAty.kt")
        assertTrue("el 4K se separa de la lista inicial", aty.contains("val nativeMedias = medias.filterNot { isExternal4k(it) }"))
        assertTrue("el 4K solo se agrega al selector", aty.contains("if (external4k != null) curMedias.add(external4k)"))
        assertTrue("el respaldo automático salta el 4K", aty.contains("val skipExternal = isExternal4k(candidate)"))
        assertTrue("el respaldo por falta de primer cuadro baja de resolución", aty.contains("val skipSameTier = reason == \"sin-primer-cuadro\""))
        assertTrue("el motor nunca recibe el 4K como pista", aty.contains("specs.filterNot { isExternal4k(it) }"))
    }

    @Test
    fun the4kUsesTheCatalogSubtitleApiAndASeparateSubtleBadge() {
        val aty = source("app/src/main/java/com/chronosbvrx/visionplus/ui/player/X4KPlayerAty.kt")
        assertTrue("hay pistas de la nube en el selector", aty.contains("\\u00b7 nube"))
        assertTrue("el español se activa solo si el audio no es español", aty.contains("maybeAutoExternalSubs()"))
        assertTrue("el chip 4K es aparte de la píldora de calidad", aty.contains("buildFourKBadge(root)"))
        assertTrue("el chip es sutil (borde fino)", aty.contains("setStroke(dp(1), 0xFF4C9BFF.toInt())"))
        assertTrue("el aviso dice idioma original", aty.contains("disponible en 4K en su idioma original"))
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
    fun bothTitleSheetsShowTheBrandedBadgeWithoutResolvingBeforePlay() {
        for (path in listOf(
            "app/src/main/java/com/chronosbvrx/visionplus/ui/title/TitleScreen.kt",
            "app/src/main/java/com/chronosbvrx/visionplus/ui/tv/TvTitleScreen.kt",
        )) {
            val src = source(path)
            assertTrue("$path ya no resuelve antes del play", !src.contains("refreshIfStale"))
            assertTrue("$path muestra el badge", src.contains("FourKBadge("))
            assertTrue("$path solo enciende el badge con 2160p", src.contains("fresh4k(context, film.id)"))
        }
        val badge = source("app/src/main/java/com/chronosbvrx/visionplus/ui/components/FourKBadge.kt")
        assertTrue(badge.contains("VisionBlue"))
        assertTrue(badge.contains("ULTRA HD"))
    }

    @Test
    fun the4kResolverAndBadgeWaitForPlaybackToStart() {
        val aty = source("app/src/main/java/com/chronosbvrx/visionplus/ui/player/X4KPlayerAty.kt")
        assertTrue("no se busca 4K durante la carga", !aty.contains("handler.postDelayed(fourKPoller, 2_000L)"))
        assertTrue("el poller arranca tras el primer cuadro", aty.contains("handler.postDelayed(fourKPoller, 4_000L)"))
        assertTrue("hay resolvedor diferido", aty.contains("scheduleExternal4kResolve()"))
        assertTrue("el resolvedor corre en segundo plano", aty.contains("refreshNow(applicationContext, film)"))
        assertTrue("el chip espera unos segundos de reproducci?n", aty.contains("System.currentTimeMillis() - firstFrameAtMs < 4_000L"))
    }

    @Test
    fun aDeepLinkedFilmWithoutKindStillShowsAndPlaysTheExternalSource() {
        // Las fichas ya no resuelven: solo leen la cache, asi que aceptan cualquier kind.
        for (path in listOf(
            "app/src/main/java/com/chronosbvrx/visionplus/ui/title/TitleScreen.kt",
            "app/src/main/java/com/chronosbvrx/visionplus/ui/tv/TvTitleScreen.kt",
        )) {
            val src = source(path)
            assertTrue("$path lee la cache sin filtrar kind", src.contains("fresh4k(context, film.id)"))
        }
        val player = source("app/src/main/java/com/chronosbvrx/visionplus/ui/player/PlayerScreen.kt")
        assertTrue(
            "el reproductor debe aceptar kind vacio (deep links del launcher)",
            player.contains("kind.isBlank() || ") && player.contains("kind == \"movie\""),
        )
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
